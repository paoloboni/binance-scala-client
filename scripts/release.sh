#!/usr/bin/env bash

: "${SONATYPE_USERNAME?must be defined}"
: "${SONATYPE_PASSWORD?must be defined}"

export GPG_TTY=$(tty)
sbt "release with-defaults skip-tests"
