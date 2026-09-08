FROM mcr.microsoft.com/playwright:v1.51.1-noble
WORKDIR /e2e

COPY package.json ./
RUN npm install --no-package-lock --no-audit --no-fund

COPY playwright.config.mjs smoke.mjs performance.mjs ./
COPY tests ./tests
RUN mkdir -p /e2e/artifacts

CMD ["npm", "run", "e2e"]
