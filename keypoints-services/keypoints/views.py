from rest_framework import generics
from rest_framework.exceptions import APIException

from keypoints.models import Transcript
from keypoints.serializers import TranscriptListSerializer, TranscriptSerializer


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


# ------------------------------ EXCEPTIONS --------------------------------------------

class TranscriptAlreadyExists(APIException):
    """Business exception: the same transcript text was already saved."""
    status_code = 409
    default_detail = 'A transcript with the same text already exists.'
    default_code = 'transcript_already_exists'
