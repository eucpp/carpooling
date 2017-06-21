#!/bin/sh

dot graph.dot -Tps -o graph.ps
convert -flatten -density 150 -geometry 100% graph.ps graph.png
rm graph.ps