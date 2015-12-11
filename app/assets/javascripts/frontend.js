import { API } from './api.js';
import { Chart } from './chart.js';
import { WebSocket } from './websocket.js';

export class Frontend {

    constructor() {
        this.chart = new Chart();
        this.$search = $('.search');
    }

    handleScrolling() {
        var $window = $(window);
        var $navbar = $('.navbar');
        var $pushDown = $('.push-down');
        var $headings = $('.headings');
        var $searchBox = $('.search');
        var height;

        var onResize = () => {
            height = $window.innerHeight();
        };

        var onScroll = () => {
            var scrollTop = $window.scrollTop();

            if (scrollTop > 180) {
                $pushDown.addClass('push-down-small');
                $navbar.removeClass('navbar-top').addClass('show-heading');
                $headings.addClass('invisible');
                $searchBox.addClass('show-results');
            } else {
                $pushDown.removeClass('push-down-small');
                $navbar.addClass('navbar-top').removeClass('show-heading');
                $headings.removeClass('invisible');
                $searchBox.removeClass('show-results');
            }

            if (scrollTop > 250) {
                $navbar.addClass('navbar-background');
            } else {
                $navbar.removeClass('navbar-background');
            }
        };

        $window.on('scroll', onScroll).on('resize', onResize);

        onScroll();
        onResize();
    }

    /**
     * Clear the card list
     */
    resetCardList($cardListContainer) {
        while ($cardListContainer.find('twitter-cardlist')[0].pop('elements') != null);
    }

    setupWebSockets() {
        var eventToast = document.querySelector('#event-toast');
        var metrics = document.querySelector("trainer-metrics");
        var $search = $('.search');

        new WebSocket("#socket").on("onopen", (socket) => {
            console.log('Establish connection');
        }).on('onmessage', (socket, message) => {
            console.log(message.detail);
            eventToast.text = message.detail;
            eventToast.show();
        });

        new WebSocket('#statisticsSocket').on('onopen', () => {
            console.log('opened connection to statistics server');
        }).on('onmessage', (socket, message) => {
            var data = JSON.parse(message.detail);

            console.log(data);
            metrics.update(data);

            if (data.trainer == "Online") {
                this.chart.push(data.accuracy);
            } else {
                $search
                    .find('paper-input').prop('disabled', null)
                    .end()
                    .find('.message').velocity('fadeOut')
            }
        });
    }

    showResults($listContainer, results) {
        var negative = 0;
        var positive = 0;

        var cardList = $listContainer.find('twitter-cardlist')[0];

        results.forEach((item) => {
            if (item.sentiment < 0.51) {
                ++negative;
            } else {
                ++positive;
            }

            cardList.push('elements', item);
        });

        var ratio = 0;
        var sentiment = "";
        var sum = negative + positive;

        if (negative > positive) {
            ratio = negative / sum;
            sentiment = 'negative';
        } else {
            ratio = positive / sum;
            sentiment = 'positive';
        }

        ratio = (Math.round(ratio * 10000) / 100).toString().replace('.', ',');

        $listContainer
            .find('.precision')
            .text(`(${ratio}%)`)
            .end()
            .removeClass('positive negative')
            .addClass(sentiment);
    }

    wire() {
        var $batchTwitterList = $('.batch-results');
        var $onlineTwitterList = $('.online-results');

        var $body = $('body');
        var $content = $('.content');
        var progressBar = document.querySelector('.paper-progress');
        var searchForm = document.querySelector('.search');
        var searchBox = document.querySelector('paper-input');
        var container = document.querySelector('#mainContainer');

        this.handleScrolling();
        this.chart.wire();

        this.setupWebSockets();

        /**
         * Check if the user is authenticated. If not show the login form
         */
        API.authenticated().then(
            () => $body.addClass('authenticated').removeClass('not-authenticated'),
            () => console.log("failed")
        );

        /*
         * Wire the frontend
         */
        searchForm.addEventListener('submit', (event) => {
            event.preventDefault();

            this.resetCardList($batchTwitterList);
            this.resetCardList($onlineTwitterList);

            $content.velocity("scroll", {duration: 350, offset: -175});

            API.classify(searchBox.value)
                .then((json) => {
                    var batchResults = json.batchModelResult;
                    var onlineResults = json.onlineModelResult;

                    this.showResults($batchTwitterList, batchResults);
                    this.showResults($onlineTwitterList, onlineResults);

                    setTimeout(done, 0);
                })
                .catch((ex) => {
                    console.log(ex);
                });
        });

        // fade out elements
        $.Velocity(progressBar, "fadeOut", {duration: 0});

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
                    $.Velocity(paperCards[n], "fadeIn", {
                        duration: duration,
                        display: 'inline-block'
                    });

                    setTimeout(() => fadeIn(++n, duration), duration / 3);
                }
            };

            // start fading in
            fadeIn(0, 300);

            // fade out the paper-progress
            $.Velocity(progressBar, "fadeOut", {duration: 150});
        };
    }

}