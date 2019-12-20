def job = hudson.model.Hudson.instance.getItemByFullName('seed-job')
hudson.model.Hudson.instance.queue.schedule(job, 0)
