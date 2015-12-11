import { Routes } from './routes.js';

export class API {

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
    static _checkStatus(response) {
        if (response.status >= 200 && response.status < 300) {
            return response;
        } else {
            var error = new Error(response.statusText);
            error.response = response;
            throw error;
        }
    }

    static _reject(reject) {
        return (ex) => {
            console.error(ex );
            reject(ex);
        };
    }

    static get(route) {
        return new Promise((resolve, reject) => {
            fetch(route)
                .then(API._checkStatus)
                .then(resolve)
                .catch(API._reject(reject));
        });
    }

    static getAuthenticated(route) {
        return new Promise((resolve, reject) => {
            fetch(route, {
                credentials: 'same-origin'
            })  .then(API._checkStatus)
                .then(resolve)
                .catch(API._reject(reject));
        });
    }

    static simpleRequest(route) {
        return new Promise((resolve, reject) => {
            API.get(route)
                .then(resolve)
                .catch(reject)
        });
    }

    static simpleAuthenticatedRequest(route) {
        return new Promise((resolve, reject) => {
            API.getAuthenticated(route)
                .then(resolve)
                .catch(reject)
        });
    }

    static authenticated() {
        return API.simpleAuthenticatedRequest(Routes.authenticated);
    }

    /**
     * calls the classify action and returns a promise
     * the promise will be resolved with the parsed json or rejected if there was an error
     *
     * @param keyword
     */
    static classify(keyword) {
        return new Promise((resolve, reject) => {
            API.getAuthenticated(Routes.classify(keyword))
                .then((response) => response.json())
                .then(resolve)
                .catch(reject)
        });
    }

    static logout() {
        return API.simpleAuthenticatedRequest(Routes.logout);
    }

}
