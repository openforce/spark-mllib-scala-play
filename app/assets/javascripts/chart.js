export class Chart {

    /**
     * Setup the chart and wire it to the frontend
     */
    wire() {
        this.updateInterval = 1000;
        this.totalPoints = 100;
        this.data = [];

        var chartOptions = {
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
            },
            grid: {
                borderWidth: 0
            }
        };

        var timeout;
        var $plotBig = $(".realtime-chart-big-container");
        var $window = $(window);

        var handleMouseClick = (event) => {
            if ($plotBig.is(":hidden")) {
                $plotBig.velocity('fadeIn', {
                    duration: 300
                });
            } else {
                $plotBig.velocity('fadeOut', {
                    duration: 300
                });
            }
        };

        var handleMouseOver = (event) => {
            if (event.type === "mouseover") {
                timeout = setTimeout(() => {
                    $plotBig.height($window.innerHeight() - $plotBig.offset().y).velocity('fadeIn', {
                        duration: 300
                    });
                }, 1000);
            } else {
                clearTimeout(timeout);

                $plotBig.velocity('fadeOut', {
                    duration: 300
                });
            }
        };

        //$(".realtime-chart").on('click', handleMouseClick);
        $(".realtime-chart").on('mouseover mouseout', handleMouseOver);

        this.plot = $.plot(".realtime-chart", [ this.data ], chartOptions);
        this.plotBig = $.plot(".realtime-chart-big", [ this.data ], chartOptions);

        this.draw();
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

            this.plotBig.setData(data);
            this.plotBig.draw();

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
