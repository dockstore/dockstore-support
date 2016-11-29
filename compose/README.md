## Prereqs

Install [docker-compose](https://docs.docker.com/compose/install/) on a Ubuntu 16.04+ VM and it's dependencies.

**NOTE:** this isn't production ready, there is some manual config you need to do. Read all the directions below before running.


## Usage

To run the webservice, postgres, the ui 

    docker-compose build
    docker-compose up

To login to a client terminal

    docker-compose run client

To browse to the site, open [http://localhost](http://localhost)

   

