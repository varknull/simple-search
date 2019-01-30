loadtest:
	time xargs curl -s  < queries.txt > /dev/null
run:
	./docker/run.sh
stop:
	docker-compose --file ${WORKING_DIR}/docker/docker-compose.yml down
