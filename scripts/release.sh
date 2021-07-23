#!/usr/bin/env bash

: "${SONATYPE_USERNAME?must be defined}"
: "${SONATYPE_PASSWORD?must be defined}"

export GPG_TTY=$(tty)
sbt "release cross with-defaults skip-tests"
sbt "++3.0.0 doc ghpagesPushSite"
