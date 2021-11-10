FROM python:3.10

RUN mkdir /pip_cache
WORKDIR /app
COPY server/requirements.txt .
RUN --mount=type=cache,target=/pip_cache \
    pip install \
        --cache-dir /pip_cache \
        -r requirements.txt

COPY test_server.proto .
RUN python -m grpc_tools.protoc --python_out=. --grpc_python_out=. -I=. test_server.proto

COPY server/server.py .
ENTRYPOINT [ "python", "server.py" ]
