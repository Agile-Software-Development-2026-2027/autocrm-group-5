from django.urls import path

from keypoints.views import TranscriptDetail, TranscriptListCreate

urlpatterns = [
    path('', TranscriptListCreate.as_view()),           # GET (list) and POST /api/v1/transcripts/
    path('<int:pk>/', TranscriptDetail.as_view()),      # GET /api/v1/transcripts/7/
]
