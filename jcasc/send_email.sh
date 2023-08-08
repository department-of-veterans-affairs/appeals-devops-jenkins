#!/bin/bash

echo "Starting sendmail command `date`"

dmy=$(date '+%m/%d/%Y')
tod=$1

if  [ $tod == 'morning' ] ; then
(echo To: E_Appeals_SystemTeam@bah.com; echo From: appeals.health@noreply.va.gov; echo "Content-Type: text/html; "; echo Subject: Appeals System Team Morning Health Check - $dmy; echo; cat /var/lib/jenkins/morning_email.html;) | /sbin/sendmail -t
fi

if  [ $tod == 'evening' ] ; then
(echo To: E_Appeals_SystemTeam@bah.com; echo From: appeals.health@noreply.va.gov; echo "Content-Type: text/html; "; echo Subject: Appeals System Team Evening Health Check - $dmy; echo; cat /var/lib/jenkins/evening_email.html;) | /sbin/sendmail -t
fi

echo "finished sendmail command"