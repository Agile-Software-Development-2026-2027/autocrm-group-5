import json
from unittest.mock import MagicMock, patch

import httpx2 as httpx
import openai

from django.conf import settings
from rest_framework import status
from rest_framework.test import APITestCase

from keypoints.models import Transcript

VALID_RESULT = {
    'actionPoints': [{'title': 'Sconto 10% confermato', 'description': None}],
    'tasks': [{'title': 'Preparare contratto', 'description': None,
               'assignee': 'Laura', 'dueDate': '2026-11-28', 'priority': 'HIGH'}],
}

TRANSCRIPTS_URL = '/api/v1/transcripts/'
EXTRACTIONS_URL = '/api/v1/extractions/'


class TranscriptListTests(APITestCase):
    """GET /api/v1/transcripts/ -> paginated {count, results} (contract with the backend)."""

    def setUp(self):
        Transcript.objects.create(text='Anna: the integration must be live by January.')
        Transcript.objects.create(text='Luca: two meetings were not synchronised.')

    def test_list_returns_200_paginated(self):
        response = self.client.get(TRANSCRIPTS_URL)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['count'], 2)
        self.assertEqual(len(response.data['results']), 2)

    def test_detail_returns_200(self):
        t = Transcript.objects.first()
        response = self.client.get(f'{TRANSCRIPTS_URL}{t.id}/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['id'], t.id)


def _auth():
    return {'HTTP_KEYPOINTS_API_KEY': settings.KEYPOINTS_API_KEY}


def _llm_answer(content):
    """Fake OpenAI client whose chat completion answers with `content` (no real LLM call, no quota)."""
    client = MagicMock()
    client.chat.completions.create.return_value.choices = [MagicMock(message=MagicMock(content=content))]
    return client


def _http_error(cls, status_code, headers=None):
    response = httpx.Response(status_code, headers=headers or {}, request=httpx.Request('POST', 'http://llm'))
    return cls('error', response=response, body=None)


class ExtractionAuthTests(APITestCase):
    """The internal API key is checked BEFORE the body (security first)."""

    @patch('keypoints.views.extract_action_items')
    def test_missing_key_returns_401_without_calling_the_llm(self, mock_llm):
        response = self.client.post(EXTRACTIONS_URL, {'transcript': 'x'}, format='json')
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)
        self.assertEqual(response.json(), {'error': 'Invalid or missing API key.', 'code': 'unauthorized'})
        mock_llm.assert_not_called()

    def test_wrong_key_returns_401(self):
        response = self.client.post(
            EXTRACTIONS_URL, {'transcript': 'x'}, format='json', HTTP_KEYPOINTS_API_KEY='wrong-key')
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)
        self.assertEqual(response.json()['code'], 'unauthorized')

    def test_wrong_key_is_checked_before_the_body(self):
        response = self.client.post(EXTRACTIONS_URL, {}, format='json', HTTP_KEYPOINTS_API_KEY='wrong-key')
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class ExtractionBodyTests(APITestCase):
    """With a valid key, the body is validated by ExtractionRequestSerializer (400 + clear message)."""

    def _assert_400(self, payload, field):
        with patch('keypoints.views.extract_action_items') as mock_llm:
            response = self.client.post(EXTRACTIONS_URL, payload, format='json', **_auth())
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertEqual(response.json()['code'], 'invalid_request')
        self.assertIn(field, response.json()['error'])
        mock_llm.assert_not_called()

    def test_missing_transcript(self):
        self._assert_400({'language': 'it'}, 'transcript')

    def test_empty_transcript(self):
        self._assert_400({'transcript': ''}, 'transcript')

    def test_blank_transcript(self):
        self._assert_400({'transcript': '   '}, 'transcript')

    def test_invalid_language(self):
        self._assert_400({'transcript': 'x', 'language': 'fr'}, 'language')

    def test_invalid_meeting_date(self):
        self._assert_400({'transcript': 'x', 'meetingDate': '27/11/2026'}, 'meetingDate')

    def test_wrong_content_type_returns_400_not_415(self):
        response = self.client.post(EXTRACTIONS_URL, 'transcript=x', content_type='text/plain', **_auth())
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertEqual(response.json()['code'], 'invalid_request')


@patch('keypoints.services.OpenAI')
class ExtractionLlmTests(APITestCase):
    """The LLM provider is always mocked: CI never calls a real LLM."""

    def setUp(self):
        # The key is only read from the environment: give the tests a fake one
        override = self.settings(LLM_API_KEY='fake-key-for-tests')
        override.enable()
        self.addCleanup(override.disable)

    def _post(self, payload=None):
        return self.client.post(
            EXTRACTIONS_URL, payload or {'transcript': 'Marco: ok', 'language': 'it'}, format='json', **_auth())

    def test_valid_transcript_returns_200_with_the_schema(self, mock_openai):
        mock_openai.return_value = _llm_answer(json.dumps(VALID_RESULT))
        response = self._post({
            'transcript': 'Marco: confermiamo lo sconto del 10%. Laura: preparo il contratto.',
            'language': 'it', 'meetingTitle': 'Rinnovo ACME', 'meetingDate': '2026-11-27'})
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        body = response.json()
        self.assertEqual(set(body), {'actionPoints', 'tasks', 'language', 'model'})
        self.assertEqual(body['tasks'][0]['assignee'], 'Laura')
        self.assertEqual(body['tasks'][0]['dueDate'], '2026-11-28')
        self.assertEqual(body['actionPoints'][0]['title'], 'Sconto 10% confermato')
        self.assertEqual(body['language'], 'it')
        self.assertEqual(body['model'], settings.LLM_MODEL)

    def test_empty_result_is_a_success(self, mock_openai):
        mock_openai.return_value = _llm_answer(json.dumps({'actionPoints': [], 'tasks': []}))
        response = self._post()
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.json()['tasks'], [])

    def test_rate_limit_returns_429_with_retry_after(self, mock_openai):
        client = MagicMock()
        client.chat.completions.create.side_effect = _http_error(
            openai.RateLimitError, 429, {'retry-after': '30'})
        mock_openai.return_value = client
        response = self._post()
        self.assertEqual(response.status_code, status.HTTP_429_TOO_MANY_REQUESTS)
        self.assertEqual(response.json()['code'], 'rate_limited')
        self.assertEqual(response['Retry-After'], '30')

    def test_provider_5xx_returns_502_llm_unavailable(self, mock_openai):
        client = MagicMock()
        client.chat.completions.create.side_effect = _http_error(openai.InternalServerError, 503)
        mock_openai.return_value = client
        response = self._post()
        self.assertEqual(response.status_code, status.HTTP_502_BAD_GATEWAY)
        self.assertEqual(response.json()['code'], 'llm_unavailable')

    def test_provider_timeout_returns_502_llm_unavailable(self, mock_openai):
        client = MagicMock()
        client.chat.completions.create.side_effect = openai.APITimeoutError(
            request=httpx.Request('POST', 'http://llm'))
        mock_openai.return_value = client
        response = self._post()
        self.assertEqual(response.status_code, status.HTTP_502_BAD_GATEWAY)
        self.assertEqual(response.json()['code'], 'llm_unavailable')

    def test_malformed_json_returns_502_llm_invalid_output_after_one_retry(self, mock_openai):
        mock_openai.return_value = _llm_answer('this is {not json')
        response = self._post()
        self.assertEqual(response.status_code, status.HTTP_502_BAD_GATEWAY)
        self.assertEqual(response.json()['code'], 'llm_invalid_output')
        self.assertEqual(mock_openai.return_value.chat.completions.create.call_count, 2)

    def test_output_not_matching_the_schema_returns_502(self, mock_openai):
        mock_openai.return_value = _llm_answer(json.dumps({'actionPoints': 'nope'}))
        response = self._post()
        self.assertEqual(response.status_code, status.HTTP_502_BAD_GATEWAY)
        self.assertEqual(response.json()['code'], 'llm_invalid_output')

    def test_invalid_output_then_valid_output_succeeds_on_retry(self, mock_openai):
        bad = _llm_answer('garbage').chat.completions.create.return_value
        good = _llm_answer(json.dumps(VALID_RESULT)).chat.completions.create.return_value
        client = MagicMock()
        client.chat.completions.create.side_effect = [bad, good]
        mock_openai.return_value = client
        self.assertEqual(self._post().status_code, status.HTTP_200_OK)

    def test_missing_llm_api_key_returns_502_without_crashing(self, mock_openai):
        with self.settings(LLM_API_KEY=''):
            response = self._post()
        self.assertEqual(response.status_code, status.HTTP_502_BAD_GATEWAY)
        self.assertEqual(response.json()['code'], 'llm_unavailable')
        mock_openai.assert_not_called()
