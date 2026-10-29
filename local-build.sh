#!/bin/bash

./gradlew build javadoc -x checkstyleMain -x checkstyleTest -Pidw.jakarta.iiqpath=./lib-9.0/sailpoint
