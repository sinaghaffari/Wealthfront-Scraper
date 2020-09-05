ARG OPENJDK_TAG=16
FROM mozilla/sbt:latest
COPY project/ /wf-scraper/project
COPY src/ /wf-scraper/src/
COPY build.sbt /wf-scraper/build.sbt
RUN mkdir /wf-scraper/asset_overviews
WORKDIR /wf-scraper
ENTRYPOINT sbt run
