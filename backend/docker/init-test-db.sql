-- docker-compose 가 처음 DB 를 만들 때 한 번 실행된다. 통합 테스트는 이 DB 를 매번 비우고 다시 migrate 한다.
CREATE DATABASE recarry_test;
