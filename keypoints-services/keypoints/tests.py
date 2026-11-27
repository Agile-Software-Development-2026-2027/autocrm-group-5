import json
from unittest.mock import patch

from django.conf import settings
from rest_framework import status
from rest_framework.test import APITestCase

from keypoints.models import Transcript

TRANSCRIPTS_URL = '/api/v1/transcripts/'
EXTRACTIONS_URL = '/api/v1/transcripts/extractions/'


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


class ExtractionAuthTests(APITestCase):
    """The internal API key is checked BEFORE the body (security first)."""

    def test_missing_key_is_rejected(self):
        response = self.client.post(EXTRACTIONS_URL, {'transcript': 'x'}, format='json')
        # DRF AuthenticationFailed without an authenticator -> 403
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_wrong_key_is_rejected(self):
        response = self.client.post(
            EXTRACTIONS_URL, {'transcript': 'x'}, format='json',
            HTTP_KEYPOINTS_API_KEY='wrong-key',
        )
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    @patch('keypoints.views.extract_action_items')
    def test_valid_key_is_accepted_and_calls_the_llm(self, mock_llm):
        mock_llm.return_value = json.dumps({'actionPoints': [], 'tasks': []})
        response = self.client.post(
            EXTRACTIONS_URL, {'transcript': 'Marco: ok'}, format='json',
            HTTP_KEYPOINTS_API_KEY=settings.KEYPOINTS_API_KEY,
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        mock_llm.assert_called_once()


class ExtractionBodyTests(APITestCase):
    """With a valid key, the body is validated by ExtractionRequestSerializer."""

    def _auth(self):
        return {'HTTP_KEYPOINTS_API_KEY': settings.KEYPOINTS_API_KEY}

    @patch('keypoints.views.extract_action_items')
    def test_missing_transcript_returns_400_without_calling_the_llm(self, mock_llm):
        response = self.client.post(EXTRACTIONS_URL, {'language': 'it'}, format='json', **self._auth())
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        mock_llm.assert_not_called()

    @patch('keypoints.views.extract_action_items')
    def test_invalid_language_returns_400(self, mock_llm):
        response = self.client.post(
            EXTRACTIONS_URL, {'transcript': 'x', 'language': 'fr'}, format='json', **self._auth(),
        )
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        mock_llm.assert_not_called()

    @patch('keypoints.views.extract_action_items')
    def test_full_request_returns_the_llm_payload(self, mock_llm):
        mock_llm.return_value = json.dumps({
            'actionPoints': [{'title': 'Sconto 10% confermato', 'description': None}],
            'tasks': [{'title': 'Preparare contratto', 'description': None,
                       'assignee': 'Laura', 'dueDate': '2026-11-28', 'priority': 'HIGH'}],
        })
        payload = {
            'transcript': 'Marco: confermiamo lo sconto del 10%. Laura: preparo il contratto.',
            'language': 'it',
            'meetingTitle': 'Rinnovo ACME',
            'meetingDate': '2026-11-27',
        }
        response = self.client.post(EXTRACTIONS_URL, payload, format='json', **self._auth())
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['tasks'][0]['assignee'], 'Laura')
        self.assertEqual(len(response.data['actionPoints']), 1)
        # The transcript was forwarded to the LLM layer
        args = mock_llm.call_args.args
        self.assertIn('Marco', args[0])
