<template>
  <FeatherExpansionPanel
    id="thread-pool-expansion"
    class="expansion-panel"
    v-model="threadPoolsActive"
  >
    <template v-slot:title>
      <div class="title-flex">
        <div class="title">Thread Pools</div>
        <div v-if="!threadPoolsActive">
          <FeatherChipList label="">
            <FeatherChip v-if="unTouchedThreadPoolData.importThreads">
              <template v-slot:icon>{{ unTouchedThreadPoolData.importThreads }}</template
              >Import Threads
            </FeatherChip>
            <FeatherChip v-if="unTouchedThreadPoolData.scanThreads">
              <template v-slot:icon>{{ unTouchedThreadPoolData.scanThreads }}</template
              >Scan Threads
            </FeatherChip>
            <FeatherChip v-if="unTouchedThreadPoolData.rescanThreads">
              <template v-slot:icon>{{ unTouchedThreadPoolData.rescanThreads }}</template
              >Rescan Threads
            </FeatherChip>
            <FeatherChip v-if="unTouchedThreadPoolData.writeThreads">
              <template v-slot:icon>{{ unTouchedThreadPoolData.writeThreads }}</template
              >Write Threads
            </FeatherChip>
          </FeatherChipList>
        </div>
      </div>
    </template>
    <div>
      <p class="pb-xl">
        Thread pool sizes impact the performance of the provisioning subsystem. Larger systems may require larger
        values. To adjust them, type a new number in the field or use the up/down arrows to select a value.
      </p>
      <FeatherInput
        :error="getError('importThreads')"
        type="number"
        label="Import"
        hint="Number of threads to allocate for requisition import tasks."
        v-model="threadPoolData.importThreads"
        @keypress="enterCheck"
      />
      <FeatherInput
        :error="getError('scanThreads')"
        type="number"
        label="Scan"
        hint="Number of threads to allocate for manual scanning tasks."
        v-model="threadPoolData.scanThreads"
        @keypress="enterCheck"
      />
      <FeatherInput
        :error="getError('rescanThreads')"
        type="number"
        label="Rescan"
        hint="Number of threads to allocate for scheduled rescanning tasks."
        v-model="threadPoolData.rescanThreads"
        @keypress="enterCheck"
      />
      <FeatherInput
        class="last-input"
        :error="getError('writeThreads')"
        type="number"
        label="Write"
        hint="Number of threads to allocate for writing to the database."
        v-model="threadPoolData.writeThreads"
        @keypress="enterCheck"
      />
      <FeatherButton
        primary
        @click="updateThreadpools"
        :disabled="loading"
      >
        <FeatherSpinner
          v-if="loading"
          class="spinner-button"
        />
        <span v-if="!loading">Update Thread Pools</span>
      </FeatherButton>
    </div>
  </FeatherExpansionPanel>
</template>

<script
  setup
  lang="ts"
>
import { useConfigurationStore } from '@/stores/configurationStore'

import { FeatherInput } from '@featherds/input'
import { FeatherButton } from '@featherds/button'
import { FeatherExpansionPanel } from '@featherds/expansion'
import { FeatherChip, FeatherChipList } from '@featherds/chips'
import { FeatherSpinner } from '@featherds/progress'
import { isEqual as _isEqual } from 'lodash'

import { putProvisionDService } from '@/services/configurationService'
import useSnackbar from '@/composables/useSnackbar'
import { threadPoolKeys } from './copy/threadPoolKeys'
import { ConfigurationHelper } from './ConfigurationHelper'

const configurationStore = useConfigurationStore()
const { showSnackBar } = useSnackbar()

const threadPoolsErrors = ref<Record<string, boolean>>({})
const threadPoolsActive = ref(false)
const loading = ref(false)

const getUpperBound = (key: string) => ['importThreads', 'writeThreads'].includes(key) ? 100 : 2000
const upperBoundErrorMessage = (upperBound: number) => `Thread pool values have to be between 1 and ${upperBound}.`
const snackbarErrorMessage = 'Thread pool values are outside of supported range.'

const threadPoolData = computed(() => {
  const localThreads: Record<string, string> = {}
  threadPoolKeys.forEach((key) => (localThreads[key] = configurationStore.provisionDService?.[key]))

  return reactive(localThreads)
})

const unTouchedThreadPoolData = computed(() => {
  const localThreads: Record<string, string> = {}
  threadPoolKeys.forEach((key) => (localThreads[key] = configurationStore.provisionDService?.[key]))

  return reactive(localThreads)
})

/** User has opted to update threadpool data.  */
const updateThreadpools = async () => {
  loading.value = true
  // Clear Errors
  threadPoolsErrors.value = {}

  // Set Current Threadpool state.
  const currentThreadpoolState = threadPoolData.value
  const updatedProvisionDData = configurationStore.provisionDService

  // Validate Threadpool Data
  threadPoolKeys.forEach((key) => {
    const val = parseInt(currentThreadpoolState?.[key])
    if (val < 1 || val > getUpperBound(key)) {
      threadPoolsErrors.value[key] = true
    }
  })

  // If there are no errors.
  if (Object.keys(threadPoolsErrors.value).length === 0) {
    try {
      // reduce provisionD data object to thread pool sizes, in order to determine whether thread pool sizes value has changed, upon update button clicked
      const reducedUpdatedProvisionDData = threadPoolKeys.reduce((acc, key) => {
        const obj: Record<string, string> = {}

        for(let elem in updatedProvisionDData) {
          if(elem === key){
            obj[elem] = updatedProvisionDData[elem]
            break
          }
        }

        return {...acc, ...obj}
      },{})
      const haveThreadPoolValuesChanged = !_isEqual(currentThreadpoolState, reducedUpdatedProvisionDData)

      // Set Update State
      threadPoolKeys.forEach((key) => {
        if (updatedProvisionDData?.[key]) {
          updatedProvisionDData[key] = parseInt(currentThreadpoolState?.[key])
        }
      })
      if (updatedProvisionDData) {
        updatedProvisionDData['requisition-def'] = ConfigurationHelper.stripOriginalIndexes(updatedProvisionDData['requisition-def'])
      }
      // Push Updates to Server
      await putProvisionDService(updatedProvisionDData)
      // Redownload + Populate Data.
      await configurationStore.getProvisionDService()

      let messageUpdateSuccess = 'Thread pool data saved.'

      if(!haveThreadPoolValuesChanged) {
        showSnackBar({
          msg: messageUpdateSuccess
        })
      } else {
        messageUpdateSuccess += ' Restart OpenNMS for this change to take effect.'

        showSnackBar({
          msg: messageUpdateSuccess,
          timeout: 10000
        })
      }
    } catch (err) {
      showSnackBar({
        msg: `Thread pool data not saved. (${err})`,
        error: true
      })
    }
  } else {
    showSnackBar({
      msg: snackbarErrorMessage,
      error: true
    })
  }

  loading.value = false
}

/**
 * Check if User has hit enter in a Threadpool box.
 * @param key They key that has been pressed.
 */
const enterCheck = (key: { key: string }) => {
  if (key.key === 'Enter') {
    updateThreadpools()
  }
}

/**
 * Determine is error is set for a key, and if so, return generic error message.
 */
const getError = (key: string) => {
  return threadPoolsErrors.value[key] ? upperBoundErrorMessage(getUpperBound(key)) : ''
}
</script>

<style
  lang="scss"
  scoped
>
@import "@featherds/styles/mixins/typography";

.expansion-panel{
  :deep(.feather-expansion-header-button) {
    height: 72px;
  }
}
.title {
  @include headline3();
  margin-right: 16px;
}
.title-flex {
  display: flex;
  align-items: center;
}
.last-input {
  margin-bottom: 10px;
}
</style>
