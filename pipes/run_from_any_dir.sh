#!/bin/bash

## Arguments
if test "$#" -ne 3; then
    cat <<EOF
Usage:    
mwk_pipes src.mwk "search term" dest.mwk
EOF
    exit;
fi
SOURCE_MWK="$PWD/$1"
SEARCH_TERM="$2"
DESTINATION_MWK="$PWD/$3"

## Checkpoint before messing with files
cd /sarnobat.garagebandbroken/Desktop/sarnobat.git/mwk
git commit --all --message="Minor changes (untested)"

cd /Users/sarnobat/github/textsorter_web/pipes/ || exit -1;

cat $SOURCE_MWK \
	| groovy mwk2json.groovy \
	| grep -v http \
	| grep -i $SEARCH_TERM \
	| groovy jsonmvmwk.groovy $SOURCE_MWK $DESTINATION_MWK
