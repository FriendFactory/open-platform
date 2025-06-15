#!/bin/bash
. /home/ec2-user/.nvm/nvm.sh
{{ verdaccio_bin_path }} --config {{ verdaccio_static_content_path }}/conf/config.yaml

