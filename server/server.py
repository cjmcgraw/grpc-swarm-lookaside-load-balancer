#! /usr/bin/env python
from concurrent import futures
import logging
import random
import time
import sys
import os

import grpc

from test_server_pb2_grpc import TestServerServicer, add_TestServerServicer_to_server
from test_server_pb2 import CallRequest, CallResponse


class TestServer(TestServerServicer):

    def CallServer(self, request: CallRequest, context):
        logging.info(request)
        return CallResponse(
            message="Hello " + str(request.callerId)
        )

if __name__ == '__main__':
    logging.basicConfig(
        level=logging.DEBUG,
        stream=sys.stdout
    )

    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    add_TestServerServicer_to_server(TestServer(), server)
    server.add_insecure_port('0.0.0.0:50051')
    server.start()
    server.wait_for_termination()
