FROM ubuntu:20.04

FROM fullstorydev
FROM golang:1.15-alpine as builder
MAINTAINER FullStory Engineering

# create non-privileged group and user
RUN addgroup -S grpcurl && adduser -S grpcurl -G grpcurl

WORKDIR /tmp/fullstorydev/grpcurl
# copy just the files/sources we need to build grpcurl
COPY VERSION *.go go.* /tmp/fullstorydev/grpcurl/
COPY cmd /tmp/fullstorydev/grpcurl/cmd
# and build a completely static binary (so we can use
# scratch as basis for the final image)
ENV CGO_ENABLED=0
ENV GO111MODULE=on
RUN go build -o /grpcurl \
    -ldflags "-w -extldflags \"-static\" -X \"main.version=$(cat VERSION)\"" \
        ./cmd/grpcurl

WORKDIR /
COPY /grpcurl /bin/grpcurl
USER grpcurl

ENTRYPOINT ["/bin/grpcurl"]

