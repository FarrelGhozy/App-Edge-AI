FROM oven/bun:1.3 AS builder

WORKDIR /app

COPY package.json bun.lock ./
RUN bun install --frozen-lockfile

COPY prisma ./prisma
RUN bunx prisma generate

COPY . .

RUN bun build src/index.ts --outdir=dist --target=bun

FROM oven/bun:1.3 AS runner

RUN apt-get update -qq && apt-get install -y -qq curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/prisma ./prisma
COPY --from=builder /app/package.json ./

RUN bunx prisma generate

EXPOSE 8150

CMD ["sh", "-c", "bunx prisma db push --accept-data-loss && bun dist/index.js"]
