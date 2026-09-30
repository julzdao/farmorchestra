FarmOrchestra is an Open Source Hydroponics Monitoring System for growing your own food in a reliable way. 

It connects sensor readings from your indoor/outdoor farm setup (such as Hydroponics), with a dashboard to help you monitor under optimal growing conditions. 

The first prototype targets a simple deep-water culture (DWC) lettuce setup.

## Current Status 

Early dev. 
For now, this monorepo contains the API, dashboard and collector applications. 

## Prerequisites 

- [Git](https://git-scm.com/install/) and [Make](https://formulae.brew.sh/formula/make) 
- Java 21 
- Node.js 24 LTS and `npm`
- [Vite](https://vite.dev)
- Python 3.12 and [uv](https://docs.astral.sh/uv/#installation)

## Quick Start 

Clone the repo with: 

```bash 
git clone git@github.com:julzdao/farmorchestra.git
cd farmorchestra
```

Install the necessary dependencies: 

```bash 
cd apps/dashboard 
npm install 
cd ../collector 
uv sync
```

Make sure to start each application in a separate terminal by running the following commands from the repository root: 

```bash 
make api
```

```bash 
make dashboard
```

```bash 
make collector
```

This should start the dashboard at `http://localhost:5173` and api at `http://localhost:8080`. 