export class WebSocket {

    constructor(wsElement) {
        this.wsElement = document.querySelector(wsElement);
    }

    on(eventType, f) {
        this.wsElement.addEventListener(eventType, function (...params) {
            f(this, ...params);
        });

        return this;
    }

    send(value) {
        this.wsElement.send(value);
    }

}
