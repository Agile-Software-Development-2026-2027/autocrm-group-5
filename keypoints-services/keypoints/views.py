import secrets

from django.conf import settings
from rest_framework import generics
from rest_framework.exceptions import APIException
from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework.views import APIView

from keypoints.errors import Unauthorized
from keypoints.models import Transcript
from keypoints.serializers import (
    ExtractionRequestSerializer,
    TranscriptListSerializer,
    TranscriptSerializer,
)
from keypoints.services import extract_action_items


class TranscriptListCreate(generics.ListCreateAPIView):
    """GET: list the transcripts (paginated, with text and key points). POST: save a transcript (no LLM call)."""
    # Newest first; pagination needs a stable order
    queryset = Transcript.objects.order_by('-created_at', '-id')

    def get_serializer_class(self):
        # GET uses a read-only serializer; POST uses the full one (validation of the text)
        if self.request.method == 'GET':
            return TranscriptListSerializer
        return TranscriptSerializer

    def perform_create(self, serializer):
        text = serializer.validated_data['text']
        # A transcript with the same text (same hash) already exists: business error 409
        if Transcript.objects.filter(text_hash=Transcript.hash_of(text)).exists():
            raise TranscriptAlreadyExists()
        serializer.save()


class TranscriptDetail(generics.RetrieveAPIView):
    """GET: return the full transcript with its key points."""
    queryset = Transcript.objects.all()
    serializer_class = TranscriptSerializer


class ExtractionView(APIView):
    """POST: validate the internal API key, then ask the LLM to extract action points and tasks."""

    def post(self, request: Request):
        # Security first: reject before touching the body (slide 09)
        self._validate_api_key(request)
        serializer = ExtractionRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data
        result = extract_action_items(
            data['transcript'], data['language'],
            data.get('meetingTitle'), data.get('meetingDate'),
        )
        return Response(result)

    def _validate_api_key(self, request: Request):
        expected_key = settings.KEYPOINTS_API_KEY
        received_key = request.headers.get('Keypoints-API-Key', '')
        # Constant-time comparison: do not leak the key through response timing
        if not secrets.compare_digest(received_key, expected_key):
            raise Unauthorized()


# ------------------------------ EXCEPTIONS --------------------------------------------

class TranscriptAlreadyExists(APIException):
    """Business exception: the same transcript text was already saved."""
    status_code = 409
    default_detail = 'A transcript with the same text already exists.'
    default_code = 'transcript_already_exists'
