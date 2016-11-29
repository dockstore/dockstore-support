#!/bin/bash

service nginx restart
tail -f /var/log/nginx/access.log
