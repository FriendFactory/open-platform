#!/bin/bash

# go to the Diectory
cd /home/ec2-user/ai/ComfyUI/

# activate the pytorch
source activate pytorch
# source /opt/pytorch/bin/activate pytorch

# start comfyui
python3 main.py --listen 0.0.0.0 --highvram --use-pytorch-cross-attention

