#!/bin/bash

unzip app.jar > /dev/null
rm BOOT-INF/classes/application.yml
rm BOOT-INF/classes/potatoes.xml

args=`ls BOOT-INF/lib/ | xargs -i echo -n ":BOOT-INF/lib/{}" | cut -c 2-`
java -classpath config:BOOT-INF/classes:$args cn.anayoo.sweetpotato.ClientApplication