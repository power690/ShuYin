#include "NNFilter.h"
#include "NNFilterCommon.h"
#include "CPUFeatures.h"

#if defined(__riscv_v_intrinsic) && (__riscv_v_intrinsic >= 11000)
    #define APE_USE_RVV_INTRINSICS
#endif

#ifdef APE_USE_RVV_INTRINSICS
    #include <riscv_vector.h> // RVV
#endif

namespace APE
{

bool GetRVVAvailable()
{
#ifdef APE_USE_RVV_INTRINSICS
    return true;
#else
    return false;
#endif
}

#ifdef APE_USE_RVV_INTRINSICS

void AdaptRVV(short * pM, const short * pAdapt, int32 nDirection, int nOrder)
{
    size_t vlmax = __riscv_vsetvlmax_e16m2();

    // figure out direction
    const vint16m2_t rvvDir = __riscv_vmv_v_x_i16m2((nDirection < 0) - (nDirection > 0), vlmax);

    for (size_t vl; nOrder > 0; nOrder -= vl, pM += vl, pAdapt += vl)
    {
        vl = __riscv_vsetvl_e16m2(nOrder);

        const vint16m2_t rvvM = __riscv_vle16_v_i16m2(pM, vl);
        const vint16m2_t rvvAdapt = __riscv_vle16_v_i16m2(pAdapt, vl);
        const vint16m2_t rvvNew = __riscv_vadd(rvvM, __riscv_vmul(rvvAdapt, rvvDir, vl), vl);

        __riscv_vse16(pM, rvvNew, vl);
    }
}

void AdaptRVV(int * pM, const int * pAdapt, int64 nDirection, int nOrder)
{
    size_t vlmax = __riscv_vsetvlmax_e32m2();

    // figure out direction
    const vint32m2_t rvvDir = __riscv_vmv_v_x_i32m2((nDirection < 0) - (nDirection > 0), vlmax);

    for (size_t vl; nOrder > 0; nOrder -= vl, pM += vl, pAdapt += vl)
    {
        vl = __riscv_vsetvl_e32m2(nOrder);

        const vint32m2_t rvvM = __riscv_vle32_v_i32m2(pM, vl);
        const vint32m2_t rvvAdapt = __riscv_vle32_v_i32m2(pAdapt, vl);
        const vint32m2_t rvvNew = __riscv_vadd(rvvM, __riscv_vmul(rvvAdapt, rvvDir, vl), vl);

        __riscv_vse32(pM, rvvNew, vl);
    }
}

static int32 CalculateDotProductRVV(const short * pA, const short * pB, int nOrder)
{
    size_t vlmax = __riscv_vsetvlmax_e32m2();

    // loop
    vint32m2_t rvvSum = __riscv_vmv_v_x_i32m2(0, vlmax);
    for (size_t vl; nOrder > 0; nOrder -= vl, pA += vl, pB += vl)
    {
        vl = __riscv_vsetvl_e32m2(nOrder);

        const vint16m1_t rvvA = __riscv_vle16_v_i16m1(pA, vl);
        const vint16m1_t rvvB = __riscv_vle16_v_i16m1(pB, vl);

        rvvSum = __riscv_vwmacc_tu(rvvSum, rvvA, rvvB, vl);
    }

    // build output
    return __riscv_vmv_x(__riscv_vredsum(rvvSum, __riscv_vmv_v_x_i32m1(0, 1), vlmax));
}

static int64 CalculateDotProductRVV(const int * pA, const int * pB, int nOrder)
{
    size_t vlmax = __riscv_vsetvlmax_e64m2();

    // loop
    vint64m2_t rvvSum = __riscv_vmv_v_x_i64m2(0, vlmax);
    for (size_t vl; nOrder > 0; nOrder -= vl, pA += vl, pB += vl)
    {
        vl = __riscv_vsetvl_e64m2(nOrder);

        const vint32m1_t rvvA = __riscv_vle32_v_i32m1(pA, vl);
        const vint32m1_t rvvB = __riscv_vle32_v_i32m1(pB, vl);

        const vint32m1_t rvvProduct = __riscv_vmul(rvvA, rvvB, vl);

        rvvSum = __riscv_vwadd_wv_tu(rvvSum, rvvSum, rvvProduct, vl);
    }

    // build output
    return __riscv_vmv_x(__riscv_vredsum(rvvSum, __riscv_vmv_v_x_i64m1(0, 1), vlmax));
}
#endif

#if defined(__riscv)
template <class INTTYPE, class DATATYPE> INTTYPE CNNFilter<INTTYPE, DATATYPE>::CompressRVV(INTTYPE nInput)
{
#ifdef APE_USE_RVV_INTRINSICS
    // figure a dot product
    INTTYPE nDotProduct = CalculateDotProductRVV(&m_rbInput[-m_nOrder], &m_paryM[0], m_nOrder);

    // calculate the output
    INTTYPE nOutput = static_cast<INTTYPE>(nInput - ((nDotProduct + m_nOneShiftedByShift) >> m_nShift));

    // adapt
    AdaptRVV(&m_paryM[0], &m_rbDeltaM[-m_nOrder], nOutput, m_nOrder);

    // update delta
    UPDATE_DELTA_NEW(nInput)

    // convert the input to a short and store it
    m_rbInput[0] = GetSaturatedShortFromInt(nInput);

    // increment and roll if necessary
    m_rbInput.IncrementSafe();
    m_rbDeltaM.IncrementSafe();

    return nOutput;
#else
    (void) nInput;
    return 0;
#endif
}

template int CNNFilter<int, short>::CompressRVV(int nInput);
template int64 CNNFilter<int64, int>::CompressRVV(int64 nInput);

template <class INTTYPE, class DATATYPE> INTTYPE CNNFilter<INTTYPE, DATATYPE>::DecompressRVV(INTTYPE nInput)
{
#ifdef APE_USE_RVV_INTRINSICS
    // figure a dot product
    INTTYPE nDotProduct = CalculateDotProductRVV(&m_rbInput[-m_nOrder], &m_paryM[0], m_nOrder);

    // calculate the output
    INTTYPE nOutput;
    nOutput = static_cast<INTTYPE>(nInput + ((nDotProduct + m_nOneShiftedByShift) >> m_nShift));

    // adapt
    AdaptRVV(&m_paryM[0], &m_rbDeltaM[-m_nOrder], nInput, m_nOrder);

    // update delta
    UPDATE_DELTA_NEW(nOutput)

    // update the input buffer
    m_rbInput[0] = GetSaturatedShortFromInt(nOutput);

    // increment and roll if necessary
    m_rbInput.IncrementSafe();
    m_rbDeltaM.IncrementSafe();

    return nOutput;
#else
    (void) nInput;
    return 0;
#endif
}

template int CNNFilter<int, short>::DecompressRVV(int nInput);
template int64 CNNFilter<int64, int>::DecompressRVV(int64 nInput);
#endif

}
