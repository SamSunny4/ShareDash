/**
 * ShareDash WebSocket Telemetry Client
 */

class TelemetryClient {
  constructor(onMessageCallback) {
    this.onMessage = onMessageCallback;
    this.ws = null;
    this.reconnectAttempts = 0;
    this.maxReconnectDelay = 5000;
    this.connect();
  }

  connect() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host || '127.0.0.1:54321';
    const wsUrl = `${protocol}//${host}/ws/telemetry`;

    try {
      this.ws = new WebSocket(wsUrl);

      this.ws.onopen = () => {
        console.log('⚡ Connected to ShareDash Live Telemetry Feed');
        this.reconnectAttempts = 0;
      };

      this.ws.onmessage = (event) => {
        try {
          const telemetry = JSON.parse(event.data);
          if (this.onMessage) {
            this.onMessage(telemetry);
          }
        } catch (e) {
          console.error('Error parsing telemetry JSON:', e);
        }
      };

      this.ws.onclose = () => {
        this.scheduleReconnect();
      };

      this.ws.onerror = (err) => {
        console.warn('WebSocket telemetry error, will retry...', err);
        this.ws.close();
      };
    } catch (err) {
      this.scheduleReconnect();
    }
  }

  scheduleReconnect() {
    this.reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(1.5, this.reconnectAttempts), this.maxReconnectDelay);
    setTimeout(() => this.connect(), delay);
  }
}

window.TelemetryClient = TelemetryClient;
