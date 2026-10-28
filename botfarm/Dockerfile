FROM node:22-alpine
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund
COPY app.js talks.js ./
CMD ["node", "app.js"]
