import type { ServerResponse } from "node:http";
import type { StreamEvent } from "../../shared/types.ts";

/** Minimal Server-Sent Events hub: the display holds one long-lived connection. */
export class EventHub {
  private clients = new Set<ServerResponse>();

  add(res: ServerResponse, initial?: StreamEvent) {
    res.writeHead(200, {
      "content-type": "text/event-stream",
      "cache-control": "no-cache, no-transform",
      connection: "keep-alive",
      "x-accel-buffering": "no",
    });
    res.write("retry: 5000\n\n");
    if (initial) this.send(res, initial);
    this.clients.add(res);
    const ping = setInterval(() => res.write(": ping\n\n"), 25_000);
    res.on("close", () => {
      clearInterval(ping);
      this.clients.delete(res);
    });
  }

  broadcast(event: StreamEvent) {
    for (const res of this.clients) this.send(res, event);
  }

  get size() {
    return this.clients.size;
  }

  private send(res: ServerResponse, event: StreamEvent) {
    res.write(`event: ${event.type}\ndata: ${JSON.stringify(event.data)}\n\n`);
  }
}
