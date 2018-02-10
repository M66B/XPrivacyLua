#!/bin/bash
. tools/config.sh

#https://github.com/mendhak/Crowdin-Android-Importer

rm -R ${project}/app/src/main/res/values-iw/
rm -R ${project}/app/src/main/res/values-ar-rBH/
rm -R ${project}/app/src/main/res/values-ar-rEG/
rm -R ${project}/app/src/main/res/values-ar-rSA/
rm -R ${project}/app/src/main/res/values-ar-rYE/
rm -R ${project}/app/src/main/res/values-nb-rNO/
rm -R ${project}/app/src/main/res/values-nn-rNO/
rm -R ${project}/app/src/main/res/values-no-rNO/

python $importer_dir/crowdin.py --p=app/src/main -a=get -i xprivacylua -k $api_key

mkdir -p ${project}/app/src/main/res/values-iw/
mkdir -p ${project}/app/src/main/res/values-ar-rBH/
mkdir -p ${project}/app/src/main/res/values-ar-rEG/
mkdir -p ${project}/app/src/main/res/values-ar-rSA/
mkdir -p ${project}/app/src/main/res/values-ar-rYE/

cp -R ${project}/app/src/main/res/values-he/* \
	${project}/app/src/main/res/values-iw/

cp -R ${project}/app/src/main/res/values-ar/* \
	${project}/app/src/main/res/values-ar-rBH/

cp -R ${project}/app/src/main/res/values-ar/* \
	${project}/app/src/main/res/values-ar-rEG/

cp -R ${project}/app/src/main/res/values-ar/* \
	${project}/app/src/main/res/values-ar-rSA/

cp -R ${project}/app/src/main/res/values-ar/* \
	${project}/app/src/main/res/values-ar-rYE/

mkdir -p ${project}/app/src/main/res/values-nb-rNO/
mkdir -p ${project}/app/src/main/res/values-nn-rNO/
mkdir -p ${project}/app/src/main/res/values-no-rNO/

cp -R ${project}/app/src/main/res/values-no/* \
	${project}/app/src/main/res/values-nb-rNO/

cp -R ${project}/app/src/main/res/values-no/* \
	${project}/app/src/main/res/values-nn-rNO/

cp -R ${project}/app/src/main/res/values-no/* \
	${project}/app/src/main/res/values-no-rNO/
