#include "NNFilter.h"
#include "NNFilterCommon.h"
#include "CPUFeatures.h"

#if defined(__ALTIVEC__)
    #define APE_USE_ALTIVEC_INTRINSICS
#endif

#ifdef APE_USE_ALTIVEC_INTRINSICS
    #include <altivec.h> // AltiVec
    #undef bool

    typedef __vector signed short int16x8_p;
    typedef __vector signed int int32x4_p;

    #if defined(_ARCH_PWR7)
    typedef __vector signed long long int64x2_p;
    #endif

    #if defined(_ARCH_PWR7)
        #define vec_ldu_short(a) vec_xl(0, (a));
    #elif APE_BYTE_ORDER == APE_BIG_ENDIAN
        #define vec_ldu_short(a) vec_perm(vec_ld(0, (a)), vec_ld(15, (a)), vec_lvsl(0, (a)))
    #else
        #define vec_ldu_short(a) { (a)[0], (a)[1], (a)[2], (a)[3], (a)[4], (a)[5], (a)[6], (a)[7] }
    #endif
#endif

namespace APE
{

bool GetAltiVecAvailable()
{
#ifdef APE_USE_ALTIVEC_INTRINSICS
    return true;
#else
    return false;
#endif
}

#ifdef APE_USE_ALTIVEC_INTRINSICS

#define ADAPT_ALTIVEC_SIMD_SHORT_ADD                         \
{                                                            \
    const int16x8_p avM = vec_ld(0, &pM[z + n]);             \
    const int16x8_p avAdapt = vec_ldu_short(&pAdapt[z + n]); \
    const int16x8_p avNew = vec_add(avM, avAdapt);           \
    vec_st(avNew, 0, &pM[z + n]);                            \
}

#define ADAPT_ALTIVEC_SIMD_SHORT_SUB                         \
{                                                            \
    const int16x8_p avM = vec_ld(0, &pM[z + n]);             \
    const int16x8_p avAdapt = vec_ldu_short(&pAdapt[z + n]); \
    const int16x8_p avNew = vec_sub(avM, avAdapt);           \
    vec_st(avNew, 0, &pM[z + n]);                            \
}

static void AdaptAltiVec(short * pM, const short * pAdapt, int32 nDirection, int nOrder)
{
    // we require that pM is aligned, allowing faster loads and stores
    ASSERT((reinterpret_cast<size_t>(pM) % 16) == 0);

    // we're working 16 elements at a time
    ASSERT((nOrder % 16) == 0);

    int z = 0, n = 0;
    if (nDirection < 0)
    {
        for (z = 0; z < nOrder; z += 16)
            EXPAND_SIMD_2(n, 8, ADAPT_ALTIVEC_SIMD_SHORT_ADD)
    }
    else if (nDirection > 0)
    {
        for (z = 0; z < nOrder; z += 16)
            EXPAND_SIMD_2(n, 8, ADAPT_ALTIVEC_SIMD_SHORT_SUB)
    }
}

#define ADAPT_ALTIVEC_SIMD_INT_ADD                       \
{                                                        \
    const int32x4_p avM = vec_ld(0, &pM[z + n]);         \
    const int32x4_p avAdapt = vec_xl(0, &pAdapt[z + n]); \
    const int32x4_p avNew = vec_add(avM, avAdapt);       \
    vec_st(avNew, 0, &pM[z + n]);                        \
}

#define ADAPT_ALTIVEC_SIMD_INT_SUB                       \
{                                                        \
    const int32x4_p avM = vec_ld(0, &pM[z + n]);         \
    const int32x4_p avAdapt = vec_xl(0, &pAdapt[z + n]); \
    const int32x4_p avNew = vec_sub(avM, avAdapt);       \
    vec_st(avNew, 0, &pM[z + n]);                        \
}

static void AdaptAltiVec(int * pM, const int * pAdapt, int64 nDirection, int nOrder)
{
#if defined(_ARCH_PWR7)
    // we require that pM is aligned, allowing faster loads and stores
    ASSERT((reinterpret_cast<size_t>(pM) % 16) == 0);

    // we're working 16 elements at a time
    ASSERT((nOrder % 16) == 0);

    int z = 0, n = 0;
    if (nDirection < 0)
    {
        for (z = 0; z < nOrder; z += 16)
            EXPAND_SIMD_4(n, 4, ADAPT_ALTIVEC_SIMD_INT_ADD)
    }
    else if (nDirection > 0)
    {
        for (z = 0; z < nOrder; z += 16)
            EXPAND_SIMD_4(n, 4, ADAPT_ALTIVEC_SIMD_INT_SUB)
    }
#else
    return Adapt(pM, pAdapt, nDirection, nOrder);
#endif
}

static int32 CalculateDotProductAltiVec(const short * pA, const short * pB, int nOrder)
{
    // we require that pB is aligned, allowing faster loads
    ASSERT((reinterpret_cast<size_t>(pB) % 16) == 0);

    // we're working 16 elements at a time
    ASSERT((nOrder % 16) == 0);

    // loop
    int32x4_p avSumLo = vec_splat_s32(0);
    int32x4_p avSumHi = vec_splat_s32(0);
    for (int z = 0; z < nOrder; z += 16)
    {
        const int16x8_p avALo = vec_ldu_short(&pA[z]);
        const int16x8_p avAHi = vec_ldu_short(&pA[z + 8]);
        const int16x8_p avBLo = vec_ld(0, &pB[z]);
        const int16x8_p avBHi = vec_ld(0, &pB[z + 8]);

        avSumLo = vec_msum(avALo, avBLo, avSumLo);
        avSumHi = vec_msum(avAHi, avBHi, avSumHi);
    }

    // build output
    int32x4_p avSum = vec_add(avSumLo, avSumHi);
    int32x4_p avShift = vec_sld(avSum, avSum, 0x8);

    avSum = vec_add(avSum, avShift);
    avShift = vec_sld(avSum, avSum, 0x4);
    avSum = vec_add(avSum, avShift);

#if defined(_ARCH_PWR7)
    return vec_extract(avSum, 0);
#else
    int32 result = 0;
    vec_ste(avSum, 0, &result);
    return result;
#endif
}

static int64 CalculateDotProductAltiVec(const int * pA, const int * pB, int nOrder)
{
#if defined(_ARCH_PWR8)
    // we require that pB is aligned, allowing faster loads
    ASSERT((reinterpret_cast<size_t>(pB) % 16) == 0);

    // we're working 8 elements at a time
    ASSERT((nOrder % 8) == 0);

    // loop
    int64x2_p avSum1Lo = vec_splats(0LL);
    int64x2_p avSum1Hi = vec_splats(0LL);
    int64x2_p avSum2Lo = vec_splats(0LL);
    int64x2_p avSum2Hi = vec_splats(0LL);
    for (int z = 0; z < nOrder; z += 8)
    {
        const int32x4_p avALo = vec_xl(0, &pA[z]);
        const int32x4_p avAHi = vec_xl(0, &pA[z + 4]);
        const int32x4_p avBLo = vec_ld(0, &pB[z]);
        const int32x4_p avBHi = vec_ld(0, &pB[z + 4]);

        const int32x4_p avProduct1 = vec_mul(avALo, avBLo);
        const int32x4_p avProduct2 = vec_mul(avAHi, avBHi);

        avSum1Lo = vec_add(avSum1Lo, vec_unpackl(avProduct1));
        avSum1Hi = vec_add(avSum1Hi, vec_unpackh(avProduct1));

        avSum2Lo = vec_add(avSum2Lo, vec_unpackl(avProduct2));
        avSum2Hi = vec_add(avSum2Hi, vec_unpackh(avProduct2));
    }

    // build output
    int64x2_p avSum = vec_add(vec_add(avSum1Lo, avSum1Hi), vec_add(avSum2Lo, avSum2Hi));
    int64x2_p avShift = vec_sld(avSum, avSum, 0x8);

    return vec_extract(vec_add(avSum, avShift), 0);
#else
    return CalculateDotProduct(pA, pB, nOrder);
#endif
}
#endif

#if defined(__ppc__) || defined(__powerpc__)
template <class INTTYPE, class DATATYPE> INTTYPE CNNFilter<INTTYPE, DATATYPE>::CompressAltiVec(INTTYPE nInput)
{
#ifdef APE_USE_ALTIVEC_INTRINSICS
    // figure a dot product
    INTTYPE nDotProduct = CalculateDotProductAltiVec(&m_rbInput[-m_nOrder], &m_paryM[0], m_nOrder);

    // calculate the output
    INTTYPE nOutput = static_cast<INTTYPE>(nInput - ((nDotProduct + m_nOneShiftedByShift) >> m_nShift));

    // adapt
    AdaptAltiVec(&m_paryM[0], &m_rbDeltaM[-m_nOrder], nOutput, m_nOrder);

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

template int CNNFilter<int, short>::CompressAltiVec(int nInput);
template int64 CNNFilter<int64, int>::CompressAltiVec(int64 nInput);

template <class INTTYPE, class DATATYPE> INTTYPE CNNFilter<INTTYPE, DATATYPE>::DecompressAltiVec(INTTYPE nInput)
{
#ifdef APE_USE_ALTIVEC_INTRINSICS
    // figure a dot product
    INTTYPE nDotProduct = CalculateDotProductAltiVec(&m_rbInput[-m_nOrder], &m_paryM[0], m_nOrder);

    // calculate the output
    INTTYPE nOutput;
    nOutput = static_cast<INTTYPE>(nInput + ((nDotProduct + m_nOneShiftedByShift) >> m_nShift));

    // adapt
    AdaptAltiVec(&m_paryM[0], &m_rbDeltaM[-m_nOrder], nInput, m_nOrder);

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

template int CNNFilter<int, short>::DecompressAltiVec(int nInput);
template int64 CNNFilter<int64, int>::DecompressAltiVec(int64 nInput);
#endif

}
