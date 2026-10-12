# FarmOrchestra

**Grow your own fresh food with hydroponics: easy, affordable and fun.**

FarmOrchestra is an open-source hydroponics monitoring system. It connects the
sensors in your indoor or outdoor setup to a dashboard that keeps you informed,
so your plants stay in optimal growing conditions.

## Vision

Anyone can grow their own fresh food at home, with confidence, no prior
expertise needed.

## Mission

Make hydroponics easy, affordable and fun with an open-source platform that
watches over your system, explains what's happening, and teaches you along
the way.

## Current Status

Early development. The first prototype targets a simple deep-water culture
(DWC) lettuce setup.

## Architecture [Work in Progress]

FarmOrchestra uses a monorepo containing the following applications: 
1. React Dashboard 
2. Java Spring Boot API 
3. Python Collector 

The collector runs on the Raspberry Pi and collects data from the sensors. These readings are persisted in a PostgreSQL database. Then the dashboard retrieves the data through the API. 

The next diagram shows the proposed MVP architecture. Some of the components listed are only planned, but not yet implemented.

![Proposed FarmOrchestra architecture](docs/architecture/architecture.png)

[Download the architecture PDF](docs/architecture/architecture.pdf)

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