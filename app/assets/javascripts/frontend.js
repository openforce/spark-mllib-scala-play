import { API } from './api.js';
import { Chart } from './chart.js';
import { WebSocket } from './websocket.js';

export class Frontend {

    constructor() {
        this.api = new API();
        this.chart = new Chart();
    }

    /**
     * Clear the card list
     */
    resetCardList() {
        while (this.twitterCardList.pop('elements') != null);
    }

    setupWebSockets() {
        var eventToast = document.querySelector('#event-toast');
        var eventLog = document.querySelector('event-log');

        new WebSocket("#socket").on("onopen", (socket) => {
            socket.send('Establish connection');
        }).on('onmessage', (socket, message) => {
            console.log(message.detail);
            eventLog.push('elements', message.detail);
            eventToast.text = message.detail;
            eventToast.show();
        });

        new WebSocket('#statisticsSocket').on('onopen', () => {
            console.log('opened connection to statistics server');
        }).on('onmessage', (socket, message) => {
            var data = JSON.parse(message.detail);

            //console.log(data.accuracy);
            this.chart.push(data.accuracy);
        });
    }

    wire() {
        this.twitterCardList = document.querySelector('twitter-cardlist');
        var progressBar = document.querySelector('.paper-progress');
        var searchForm = document.querySelector('.search-box');
        var searchBox = document.querySelector('paper-input');
        var container = document.querySelector('#mainContainer');

        this.chart.wire();
        this.setupWebSockets();

        /*
         * Wire the frontend
         */
        searchForm.addEventListener('submit', (event) => {

            event.preventDefault();

            this.resetCardList();

            this.api.classify(searchBox.value)
                .then((json) => {
                    console.log(json);
                    json.forEach((item) => this.twitterCardList.push('elements', item));
                    setTimeout(done, 0);
                })
                .catch((ex) => {
                    console.log(ex);
                });

            Velocity(searchBox.parentNode, 'scroll', {
                container: container,
                offset: -75,
                duration: 400
            });

            setTimeout(() => Velocity(progressBar, "fadeIn", {duration: 200}));
        });

        // fade out elements
        Velocity(progressBar, "fadeOut", {duration: 0});

        var done = () => {
            // get all paper cards (each paper card represents one tweet)
            var paperCards = document.querySelectorAll('.paper-card-container');

            /**
             * Fade in a single paper-card and set a timeout to fade in the next
             * @param n The n-th paper-card to fade in
             * @param duration The duration of the timeout
             */
            var fadeIn = (n, duration) => {
                if (n < paperCards.length) {
                    Velocity(paperCards[n], "fadeIn", {
                        duration: duration,
                        display: 'inline-block'
                    });

                    setTimeout(() => fadeIn(++n, duration), duration / 3);
                }
            };

            // start fading in
            fadeIn(0, 300);

            // fade out the paper-progress
            Velocity(progressBar, "fadeOut", {duration: 150});
        };
    }

}