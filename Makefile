.PHONY: infra-up infra-down backend-test model-test data-test frontend-build

infra-up:
	docker compose up -d

infra-down:
	docker compose down

backend-test:
	cd backend && mvn test

model-test:
	cd model-service && python -m pytest -q

data-test:
	cd data-pipeline && python -m pytest -q

frontend-build:
	cd frontend && npm install && npm run build
