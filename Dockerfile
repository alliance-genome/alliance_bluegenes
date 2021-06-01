ARG REG=100225593120.dkr.ecr.us-east-1.amazonaws.com
ARG DOCKER_PULL_TAG=latest

FROM ${REG}/agr_base_linux_env:${DOCKER_PULL_TAG}

COPY . .

RUN /bin/bash -c '. $HOME/.nvm/nvm.sh --no-use && \
  nvm install && \
  nvm use && \
  npm install'

RUN /bin/bash -c '. $HOME/.nvm/nvm.sh && lein uberjar'

ARG BLUEGENES_DEFAULT_SERVICE_ROOT=https://www.alliancegenome.org/alliancemine/
ENV BLUEGENES_DEFAULT_SERVICE_ROOT ${BLUEGENES_DEFAULT_SERVICE_ROOT}

EXPOSE 5000
CMD ["./run_bluegenes.sh"]
