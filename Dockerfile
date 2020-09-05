FROM mozilla/sbt:latest
COPY project/ /wf-scraper/
COPY src/ /wf-scraper/
COPY build.sbt /wf-scraper
RUN mkdir /wf-scraper/asset_overviews
WORKDIR /wf-scraper
ENTRYPOINT sbt run