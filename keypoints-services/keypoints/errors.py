"""Error contract (docs/api-contract.md, section 4): every 4xx/5xx body is {"error": ..., "code": ...}."""
from rest_framework import status
from rest_framework.exceptions import (
    APIException, AuthenticationFailed, NotAuthenticated, ParseError, UnsupportedMediaType, ValidationError,
)
from rest_framework.response import Response
from rest_framework.views import exception_handler


class Unauthorized(APIException):
    status_code = status.HTTP_401_UNAUTHORIZED
    default_detail = 'Invalid or missing API key.'
    default_code = 'unauthorized'


class LlmRateLimited(APIException):
    status_code = status.HTTP_429_TOO_MANY_REQUESTS
    default_detail = 'The LLM provider quota is exhausted, try again later.'
    default_code = 'rate_limited'

    def __init__(self, retry_after=None):
        super().__init__()
        self.retry_after = retry_after


class LlmUnavailable(APIException):
    status_code = status.HTTP_502_BAD_GATEWAY
    default_detail = 'The LLM provider is not available.'
    default_code = 'llm_unavailable'


class LlmInvalidOutput(APIException):
    status_code = status.HTTP_502_BAD_GATEWAY
    default_detail = 'The LLM answered with an invalid output.'
    default_code = 'llm_invalid_output'


def _first_message(detail):
    """'transcript: This field may not be blank.' - names the field, never echoes the input."""
    if isinstance(detail, dict):
        field, value = next(iter(detail.items()))
        return f'{field}: {_first_message(value)}'
    if isinstance(detail, list) and detail:
        return _first_message(detail[0])
    return str(detail)


def contract_exception_handler(exc, context):
    response = exception_handler(exc, context)
    if response is None:
        return None  # unexpected bug: Django answers 500 (not part of the contract)

    if isinstance(exc, (NotAuthenticated, AuthenticationFailed)):
        exc = Unauthorized()
        response.status_code = exc.status_code
    if isinstance(exc, (ValidationError, ParseError, UnsupportedMediaType)):
        # 400 also for a wrong Content-Type (instead of DRF's 415)
        code, message = 'invalid_request', _first_message(exc.detail)
        response.status_code = status.HTTP_400_BAD_REQUEST
    elif isinstance(exc, APIException):
        code, message = exc.get_codes(), str(exc.detail)
        if not isinstance(code, str):
            code = 'error'
        if response.status_code == status.HTTP_403_FORBIDDEN:
            code = 'forbidden'
    else:
        return response

    headers = {}
    if isinstance(exc, LlmRateLimited) and exc.retry_after:
        headers['Retry-After'] = str(exc.retry_after)
    return Response({'error': message, 'code': code}, status=response.status_code, headers=headers)
