FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM alpine:3.22
COPY --from=build /app/dist /dist
CMD ["sh", "-c", "rm -rf /export/* && cp -r /dist/. /export/"]
