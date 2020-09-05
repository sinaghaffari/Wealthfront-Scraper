FROM mozilla/docker-sbt
COPY project/ /wf-scraper/
COPY src/ /wf-scraper/
COPY build.sbt /wf-scraper
RUN mkdir /wf-scraper/asset_overviews
ENTRYPOINT sbt run