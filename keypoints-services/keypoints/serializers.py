from rest_framework import serializers

from keypoints.models import Transcript


class TranscriptSerializer(serializers.ModelSerializer):
    class Meta:
        model = Transcript
        # Fields exposed in JSON: text_hash and updated_at stay internal
        fields = ('id', 'text', 'key_points', 'created_at')
        # The client can only send the text: the rest is produced by the server
        read_only_fields = ('id', 'key_points', 'created_at')
        # Size first (validation order of slide 05): reject huge transcripts
        extra_kwargs = {'text': {'max_length': 100_000}}


class TranscriptListSerializer(serializers.ModelSerializer):
    """List view: same fields as the detail, read-only. Pages are limited to PAGE_SIZE items."""
    class Meta:
        model = Transcript
        fields = ('id', 'text', 'key_points', 'created_at')
        read_only_fields = fields

# No ModelSerializer here: there is no model, only input fields to validate.
class ExtractionRequestSerializer(serializers.Serializer):
    transcript = serializers.CharField(max_length=200_000)
    language = serializers.ChoiceField(
        choices=['it', 'en'],
        default='it',
        error_messages={'invalid_choice': 'Language must be "it" or "en".'},
    )
    meetingTitle = serializers.CharField(max_length=255, required=False, allow_null=True)
    meetingDate = serializers.DateField(required=False, allow_null=True)
