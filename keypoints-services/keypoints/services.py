import logging

from django.conf import settings
from openai import OpenAI

logger = logging.getLogger(__name__)


def extract_action_items(transcript, language, meeting_title, meeting_date):
    """Call the LLM (OpenAI-compatible endpoint) and return the raw JSON string of key points."""
    client = OpenAI(api_key=settings.LLM_API_KEY, base_url=settings.LLM_BASE_URL)

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
    # Never log the transcript or the full answer (personal data): only its size.
    logger.info('LLM response received (%d chars)', len(content or ''))
    return content
