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
