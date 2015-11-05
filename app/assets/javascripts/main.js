/*
 Copyright (c) 2015 The Polymer Project Authors. All rights reserved.
 This code may only be used under the BSD style license found at http://polymer.github.io/LICENSE.txt
 The complete set of authors may be found at http://polymer.github.io/AUTHORS.txt
 The complete set of contributors may be found at http://polymer.github.io/CONTRIBUTORS.txt
 Code distributed by Google as part of the polymer project is also
 subject to an additional IP rights grant found at http://polymer.github.io/PATENTS.txt
 */

(function (document) {
    'use strict';

    // Grab a reference to our auto-binding template
    // and give it some initial binding values
    // Learn more about auto-binding templates at http://goo.gl/Dx1u2g
    var app = document.querySelector('#app');

    app.displayInstalledToast = function () {
        // Check to make sure caching is actually enabled—it won't be in the dev environment.
        if (!document.querySelector('platinum-sw-cache').disabled) {
            document.querySelector('#caching-complete').show();
        }
    };

    // Listen for template bound event to know when bindings
    // have resolved and content has been stamped to the page
    app.addEventListener('dom-change', function () {
        var api = new API();
        var twitterCardList = document.querySelector('twitter-cardlist');
        var progressBar = document.querySelector('.paper-progress');
        var searchForm = document.querySelector('.search-box');
        var searchBox = document.querySelector('paper-input');
        var container = document.querySelector('#mainContainer');

        var eventToast = document.querySelector('#event-toast');
        var eventLog = document.querySelector('event-log');
        var wsElement = document.querySelector("ws-element");
        wsElement.addEventListener('onerror', function (error){
            throw new Error(error);
        });
        wsElement.addEventListener('onopen', function () {
            wsElement.send('Establish connection');
        });
        wsElement.addEventListener('onmessage', function (message) {
            // hello from the server
            console.log(message.detail);
            eventLog.push('elements', message.detail)
            eventToast.text = message.detail;
            eventToast.show();
        });

        /*
         * Wire the frontend
         */
        searchForm.addEventListener('submit', (event) => {

            event.preventDefault();

            clearCardList();

            api.classify(searchBox.value)
                .then((json) => {
                    console.log("Search result: " + json);
                    json.forEach((item) => twitterCardList.push('elements', item));
                })
                .catch((ex) => {
                  console.log(ex);
                });

            Velocity(searchBox.parentNode, 'scroll', {
                container: container,
                offset: -75,
                duration: 400
            });

            setTimeout(() => Velocity(progressBar, "fadeIn", { duration: 200 }));
            setTimeout(done, 1000 + Math.random() * 2000);
        });

        // fade out elements
        Velocity(progressBar, "fadeOut", { duration: 0 });

        /**
         * Clear the twitter-cardlist
         */
        var clearCardList = () => {
            while (twitterCardList.pop('elements') != null);
        };

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
            Velocity(progressBar, "fadeOut", { duration: 150 });
        };
    });

    // See https://github.com/Polymer/polymer/issues/1381
    window.addEventListener('WebComponentsReady', function () {
        // imports are loaded and elements have been registered
    });

    // Main area's paper-scroll-header-panel custom condensing transformation of
    // the appName in the middle-container and the bottom title in the bottom-container.
    // The appName is moved to top and shrunk on condensing. The bottom sub title
    // is shrunk to nothing on condensing.
    addEventListener('paper-header-transform', function (e) {
        var appName = document.querySelector('#mainToolbar .app-name');
        var middleContainer = document.querySelector('#mainToolbar .middle-container');
        var bottomContainer = document.querySelector('#mainToolbar .bottom-container');
        var detail = e.detail;
        var heightDiff = detail.height - detail.condensedHeight;
        var yRatio = Math.min(1, detail.y / heightDiff);
        var maxMiddleScale = 0.50;  // appName max size when condensed. The smaller the number the smaller the condensed size.
        var scaleMiddle = Math.max(maxMiddleScale, (heightDiff - detail.y) / (heightDiff / (1 - maxMiddleScale)) + maxMiddleScale);
        var scaleBottom = 1 - yRatio;

        // Move/translate middleContainer
        Polymer.Base.transform('translate3d(0,' + yRatio * 100 + '%,0)', middleContainer);

        // Scale bottomContainer and bottom sub title to nothing and back
        Polymer.Base.transform('scale(' + scaleBottom + ') translateZ(0)', bottomContainer);

        // Scale middleContainer appName
        Polymer.Base.transform('scale(' + scaleMiddle + ') translateZ(0)', appName);
    });

    // Close drawer after menu item is selected if drawerPanel is narrow
    app.onDataRouteClick = function () {
        var drawerPanel = document.querySelector('#paperDrawerPanel');
        if (drawerPanel.narrow) {
            drawerPanel.closeDrawer();
        }
    };

    // Scroll page to top and expand header
    app.scrollPageToTop = function () {
        document.getElementById('mainContainer').scrollTop = 0;
    };

})(document);

/**
 * mapping to global *Routes* object from play generated jsRoutes
 *
 * @type {{classify: function}}
 */
var Routes = {
    classify: (keyword) => jsRoutes.controllers.Application.classify(keyword).url
};

class API {

    constructor() {
        this.baseUrl = "/";
    }

    /**
     * from https://github.com/github/fetch#handling-http-error-statuses
     *
     * fetch doesn't automatically throw an error on error http status codes (4xx, 5xx)
     *
     * @param response
     * @returns {*}
     * @private
     */
    _checkStatus(response) {
        if (response.status >= 200 && response.status < 300) {
            return response;
        } else {
            var error = new Error(response.statusText);
            error.response = response;
            throw error;
        }
    }

    /**
     * calls the classify action and returns a promise
     * the promise will be resolved with the parsed json or rejected if there was an error
     *
     * @param keyword
     * @param cb
     */
    classify(keyword, cb) {
        var url = Routes.classify(keyword);

        return new Promise((resolve, reject) => {
            fetch(url)
                .then(this._checkStatus)
                .then((response) => response.json())
                .then((json) => resolve(json))
                .catch((ex) => {
                    console.log(`error occured ${ex}`);
                    reject(ex);
                })
        });
    }

}
