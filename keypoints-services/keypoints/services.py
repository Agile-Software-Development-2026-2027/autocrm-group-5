import json
import logging

import openai
from django.conf import settings
from openai import OpenAI

from keypoints.errors import LlmInvalidOutput, LlmRateLimited, LlmUnavailable
from keypoints.serializers import ExtractionResultSerializer

logger = logging.getLogger(__name__)


def _call_llm(transcript, language, meeting_title, meeting_date):
    """One LLM call (OpenAI-compatible endpoint). Returns the raw answer; provider errors are mapped to contract errors."""
    if not settings.LLM_API_KEY:
        # Configuration error: log it, but do not leak details to the caller
        logger.error('LLM_API_KEY is not set')
        raise LlmUnavailable()
    # 60 s per attempt; the client retries 429/5xx/connection errors with backoff (max 3 times)
    client = OpenAI(api_key=settings.LLM_API_KEY, base_url=settings.LLM_BASE_URL, timeout=60, max_retries=3)

    system_prompt = (
        "You extract action points and tasks from a meeting transcript.\n"
        f"Write all titles and descriptions in this language: {language}.\n"
        f"Meeting title: {meeting_title}. Meeting date: {meeting_date} (use it to turn "
        "relative dates like 'next Friday' into an absolute YYYY-MM-DD).\n"
        "Answer ONLY with a JSON object of this exact shape:\n"
        '{\n'
        '  "actionPoints": [{"title": "string", "description": "string or null"}],\n'
        '  "tasks": [{"title": "string", "description": "string or null", '
        '"assignee": "string or null", "dueDate": "YYYY-MM-DD or null", '
        '"priority": "LOW | MEDIUM | HIGH or null"}]\n'
        '}\n'
        "Use null when the transcript does not say a value. Never invent data. "
        "If there is nothing to extract, return empty arrays."
    )

    try:
        response = client.chat.completions.create(
            model=settings.LLM_MODEL,
            messages=[  # type: ignore
                {'role': 'system', 'content': system_prompt},
                {'role': 'user', 'content': transcript},
            ],
            response_format={'type': 'json_object'},
            temperature=0,
        )
        content = response.choices[0].message.content
    except openai.RateLimitError as e:
        retry_after = e.response.headers.get('retry-after') if e.response is not None else None
        logger.warning('LLM rate limit reached')
        raise LlmRateLimited(retry_after=int(retry_after) if retry_after and retry_after.isdigit() else None)
    except openai.OpenAIError as e:
        # Timeout, connection error, 4xx/5xx, bad credentials...: details only in the logs
        logger.error('LLM call failed: %s', type(e).__name__)
        raise LlmUnavailable()
    except (IndexError, AttributeError):
        raise LlmInvalidOutput()
    # Never log the transcript or the full answer (personal data): only its size.
    logger.info('LLM response received (%d chars)', len(content or ''))
    return content


def extract_action_items(transcript, language, meeting_title, meeting_date):
    """Ask the LLM and return a dict {actionPoints, tasks, language, model} validated against the schema.

    An output that is not valid JSON or does not match the schema is retried once, then it is rejected.
    """
    for attempt in (1, 2):
        raw = _call_llm(transcript, language, meeting_title, meeting_date)
        try:
            serializer = ExtractionResultSerializer(data=json.loads(raw or ''))
        except (TypeError, ValueError):
            logger.warning('LLM output is not valid JSON (attempt %d)', attempt)
            continue
        if serializer.is_valid():
            return {**serializer.data, 'language': language, 'model': settings.LLM_MODEL}
        logger.warning('LLM output does not match the schema (attempt %d)', attempt)
    raise LlmInvalidOutput()
