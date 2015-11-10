export class Chart {

    /**
     * Setup the chart and wire it to the frontend
     */
    wire() {
        this.updateInterval = 30;
        this.totalPoints = 300;
        this.data = [];

        window.plot = this.plot = $.plot(".chart", [ this.data ], {
            series: {
                shadowSize: 0	// Drawing is faster without shadows
            },
            yaxis: {
                min: 0,
                max: 101
            },
            xaxis: {
                show: false,
                min: 0,
                max: 100
            }
        });

        this.draw();
    }

    /**
     * Test function that generates random data
     * @returns {Array}
     */
    getRandomData() {
        if (this.data.length > 0)
            this.data = this.data.slice(1);

        // Do a random walk
        while (this.data.length < this.totalPoints) {
            var prev = this.data.length > 0 ? this.data[this.data.length - 1] : 50,
            y = prev + Math.random() * 10 - 5;

            if (y < 0) {
                y = 0;
            } else if (y > 100) {
                y = 100;
            }

            this.data.push(y);
        }

        // Zip the generated y values with the x values

        var res = [];
        for (var i = 0; i < this.data.length; ++i) {
            res.push([i, this.data[i]])
        }

        return res;
    }

    /**
     * Draw the chart continuously
     */
    draw() {
        var draw = () => {
            // create the time series array for flot
            var data = [ this.data.map((entry, index) => [ index, entry ]) ];

            this.plot.setData(data);
            this.plot.draw();

            setTimeout(draw, this.updateInterval);
        };

        draw();
    }

    /**
     * Push a new data point to the chart
     *
     * @param x
     */
    push(x) {
        this.data.push(x * 100);
    }
}
