#%%
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
#%%
df_result = pd.read_csv("/media/vinhdc/DATA/project/va-spark-vagrant/result/Vinh_Spark4VCF.csv")
#%%
cpu_percentage = [100,
178,
140,
100,
200,
300,
100,
163,
140,
100,
200,
250,
100,
200,
225,
100,
200,
300]
df_result['CPU_percentage(%)'] = cpu_percentage
df_result
#%%
fig, axs = plt.subplots(3,1, sharex=True)
fig.subplots_adjust(hspace=0)

df_pivot = pd.pivot_table(df_result, 
                          values="Running_time(minutes)", 
                          index=["CPU_cores"], 
                          columns=["Tools",], 
                          aggfunc=np.mean)
print(df_pivot)
df_pivot.round()[['GATK', 'GATK-Spark']].plot.barh(ax = axs[0], color=['red', 'green'])
df_pivot.round()[['PyPGX', 'PyPGX-Spark']].plot.barh( ax = axs[1], color=['purple', 'orange'])
df_pivot.round()[['VEP', 'VEP-Spark']].plot.barh(ax = axs[2])
axs[0].set_ylabel("")
axs[1].set_ylabel("")
axs[2].set_ylabel("")
axs[0].get_legend().remove()
axs[1].get_legend().remove()
axs[2].get_legend().remove()

fig.text(-0.01, 0.5, 'CPU cores', va='center', rotation='vertical')
fig.text(0.45, -0.01, 'Running time(minutes)', ha='center')
# fig.suptitle('Running ', fontsize=15)
fig.legend(loc=7)
fig.tight_layout()
fig.subplots_adjust(right=0.75)


# ax.bar_label(ax.containers[1], fontsize=8)
# ax.bar_label(ax.containers[0], fontsize=8)
# ax.bar_label(ax.containers[2], fontsize=8)
# ax.set_ylabel("Memory Usage(%)")


plt.savefig("time.pdf", bbox_inches='tight')
# %%
fig, axs = plt.subplots(1,3, sharey='row')
fig.add_gridspec(2, 2, hspace=0, wspace=0)
for ax in fig.get_axes():
    ax.label_outer()

df_pivot = pd.pivot_table(df_result, 
                          values="CPU_percentage(%)", 
                          index=["CPU_cores"], 
                          columns=["Tools",], 
                          aggfunc=np.mean)
print(df_pivot)
df_pivot.round()[['GATK', 'GATK-Spark']].plot.bar(ax = axs[0], color=['red', 'green'])
df_pivot.round()[['PyPGX', 'PyPGX-Spark']].plot.bar( ax = axs[1], color=['purple', 'orange'])
df_pivot.round()[['VEP', 'VEP-Spark']].plot.bar(ax = axs[2])
axs[0].set_ylabel("")
axs[1].set_ylabel("")
axs[2].set_ylabel("")
axs[0].get_legend().remove()
axs[1].get_legend().remove()
axs[2].get_legend().remove()
fig.legend(loc=7)
fig.tight_layout()
fig.subplots_adjust(right=0.75)


fig.text(-0.01, 0.5, 'CPU usage(%)', va='center', rotation='vertical')

plt.savefig("cpu.pdf", bbox_inches='tight')


# %%
import seaborn as sns

fig, axs = plt.subplots(1,3, sharey='row')
fig.add_gridspec(2, 2, hspace=0, wspace=0)
for ax in fig.get_axes():
    ax.label_outer()

df_pivot = pd.pivot_table(df_result, 
                          values="Maximum_memory_used(%)", 
                          index=["CPU_cores"], 
                          columns=["Tools",], 
                          aggfunc=np.mean)
print(df_pivot)
df_pivot.round()[['GATK', 'GATK-Spark']].plot.bar(ax = axs[0], color=['red', 'green'])
df_pivot.round()[['PyPGX', 'PyPGX-Spark']].plot.bar( ax = axs[1], color=['purple', 'orange'])
df_pivot.round()[['VEP', 'VEP-Spark']].plot.bar(ax = axs[2])
axs[0].set_ylabel("")
axs[1].set_ylabel("")
axs[2].set_ylabel("")
axs[0].get_legend().remove()
axs[1].get_legend().remove()
axs[2].get_legend().remove()
fig.legend(loc=7)
fig.tight_layout()
fig.subplots_adjust(right=0.75)
# axs[0].set_yticks(np.arange(0, 40, 20))
# axs[1].set_yticks(np.arange(0, 40, 20))
# axs[2].set_yticks(np.arange(0, 40, 20))

fig.text(-0.01, 0.5, 'Memory usage(%)', va='center', rotation='vertical')
plt.savefig("ram.pdf", bbox_inches='tight')

# %%
import seaborn as sns
import pandas as pd
import matplotlib.pyplot as plt

# Create a pivot table
df_pivot = pd.pivot_table(df_result,
                          values="Maximum_memory_used(%)",
                          index=["CPU_cores"],
                          columns=["Tools"],
                          aggfunc=np.mean)

# Sort the pivot table by the mean memory usage
df_pivot = df_pivot.sort_values(by="Maximum_memory_used(%)", ascending=False)

# Create a bar plot of the pivot table
fig, axs = plt.subplots(1, 3, sharey="row")
for ax, tool in zip(axs, df_pivot.columns):
    df_pivot[tool].plot.bar(ax=ax)
    ax.set_title(tool)

# Rotate the x-axis labels to prevent overlapping
fig.supxlabel("CPU cores", rotation=45, ha="right")
fig.text(-0.01, 0.5, "Memory usage(%)", va="center", rotation="vertical")
plt.tight_layout()
plt.savefig("ram.pdf")

