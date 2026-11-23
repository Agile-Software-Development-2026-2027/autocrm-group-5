import hashlib
import uuid

from django.db import models

# Create your models here.
class Transcript(models.Model):

    # Random UUID instead of 1, 2, 3...: ids can't be guessed or enumerated
    # comment this row if you want  use model id key
    # id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)

    # The original transcript (large amount of text)
    text = models.TextField()

    # SHA-256 fingerprint of the text: identifies the same transcript (upsert, idempotency)
    # testo breve CharField
    text_hash = models.CharField(max_length=64, unique=True, editable=False)

    # Key points produced by the LLM (a list of strings); empty until generated
    # dati strutturati(liste, dizionari)
    key_points = models.JSONField(default=list, blank=True)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)


    @staticmethod
    def hash_of(text):
        return hashlib.sha256(text.encode('utf-8')).hexdigest()


    def save(self, *args, **kwargs):
        # The hash is always computed from the text: callers can't set it
        self.text_hash = Transcript.hash_of(self.text)
        super().save(*args, **kwargs)


    def __str__(self):
        # Used by the admin site: never print the full text (personal data)
        return f'Transcript {self.id}'