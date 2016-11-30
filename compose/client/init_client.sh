#!/bin/bash

psql -h postgres -U postgres  -c "insert into enduser(id, isadmin, username) VALUES (1,true,'test@test.com') on conflict do nothing;"
psql -h postgres -U postgres  -c "insert into token(id, content, refreshtoken, tokensource, userid, username) VALUES (1,'test','','',1,'') on conflict do nothing;"


/bin/bash
