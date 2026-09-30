.PHONY: dashboard api collector

dashboard: 
	cd apps/dashboard && npm run dev

api: 
	cd apps/api && ./gradlew run

collector: 
	cd apps/collector && uv run main.py
