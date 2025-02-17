var bigDashboard;
var tabletasks;


const chartColor = "#FFFFFF";
		
server = {
  
    initDashboardPageCharts: function(data, start, end) {
		var datas = data.tasks.filter(a => {
				 var date = moment(a.start);
				 	end = moment(end);
				 	start = moment(start);
				  return (date.isAfter(start) && date.isBefore(end));
		});

		var dayCounts = datas.reduce(function (result, d) {
								    var day = moment(d.start).format("YYYY-MM-DD HH");
								    if (!result[day]) {
								        result[day] = 0;
								    }
								    result[day]++;
								    return result;
									}, {});
		
	if(bigDashboard!=null){
		bigDashboard.destroy();
	}
	
	
	
	
	if(tabletasks==null)
	{
		tabletasks =$('#tabletasks').DataTable({
                  'data' : data.tasks,
				  'responsive': true,
			  	  'order': [[ 1, "desc" ]],
		
               "columns": [
 		        	{ 
		                "data": "name",
		                "defaultContent": ""
		            },
		            { 
		                "data": "type",
		                "defaultContent": ""
		            },
 		        	{ 
		                "data": "status",
		                "defaultContent": ""
		            },
		            { 
		                "data": "created",
		                "defaultContent": "",
		                "render": function(data, type, row, meta){
		                	 if(type === 'display'){
									return new Date(data).toLocaleString();		                		 
		                	 }
		                   return data;
		                }
		            },
		            { 
		                "data": "start",
		                "defaultContent": "",
		                "render": function(data, type, row, meta){
		                	 if(type === 'display'){
									return new Date(data).toLocaleString();		                		 
		                	 }
		                   return data;
		                }
		            },
		            { 
		                "data": "end",
		                "defaultContent": "",
		                "render": function(data, type, row, meta){
		                	 if(type === 'display'){
									return new Date(data).toLocaleString();		                		 
		                	 }
		                   return data;
		                }
		            },
		            { 
		                "data": "duration",
		                "defaultContent": ""
		            }
		            
		            ]

        });
	}
	else
	{
		tabletasks.clear().rows.add(data.tasks).draw();
		
	}
	
	
 
    var ctx = document.getElementById('bigDashboardChart').getContext("2d");
  
    var gradientFill = ctx.createLinearGradient(0, 200, 0, 50);
    gradientFill.addColorStop(0, "rgba(128, 182, 244, 0)");
    gradientFill.addColorStop(1, "rgba(255, 255, 255, 0.24)");

    bigDashboard = new Chart(ctx, {
      type: 'line',
      data: {
        labels: Object.keys(dayCounts),
        datasets: [{
          label: "Data",
          borderColor: chartColor,
          pointBorderColor: chartColor,
          pointBackgroundColor: "#1e3d60",
          pointHoverBackgroundColor: "#1e3d60",
          pointHoverBorderColor: chartColor,
          pointBorderWidth: 1,
          pointHoverRadius: 7,
          pointHoverBorderWidth: 2,
          pointRadius: 5,
          fill: true,
          backgroundColor: gradientFill,
          borderWidth: 2,
          data: Object.values(dayCounts)
        }]
      },
      options: {
        layout: {
          padding: {
            left: 20,
            right: 20,
            top: 0,
            bottom: 0
          }
        },
        maintainAspectRatio: false,
        tooltips: {
          backgroundColor: '#fff',
          titleFontColor: '#333',
          bodyFontColor: '#666',
          bodySpacing: 4,
          xPadding: 12,
          mode: "nearest",
          intersect: 0,
          position: "nearest"
        },
        legend: {
          position: "bottom",
          fillStyle: "#FFF",
          display: false
        },
        scales: {
          yAxes: [{
            ticks: {
              fontColor: "rgba(255,255,255,0.4)",
              fontStyle: "bold",
              beginAtZero: true,
              maxTicksLimit: 5,
              padding: 10
            },
            gridLines: {
              drawTicks: true,
              drawBorder: false,
              display: true,
              color: "rgba(255,255,255,0.1)",
              zeroLineColor: "transparent"
            }

          }],
          xAxes: [{
            gridLines: {
              zeroLineColor: "transparent",
              display: false,

            },
            ticks: {
              padding: 10,
              fontColor: "rgba(255,255,255,0.4)",
              fontStyle: "bold"
            }
          }]
        }
      }
    });
	
	
  }
};