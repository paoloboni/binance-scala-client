#!/usr/bin/env bash

: "${SONATYPE_USERNAME?must be defined}"
: "${SONATYPE_PASSWORD?must be defined}"

export GPG_TTY=$(tty)
sbt "release cross with-defaults skip-tests"
sbt ++3.1.3 "doc;ghpagesPushSite"
