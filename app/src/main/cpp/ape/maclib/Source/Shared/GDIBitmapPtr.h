#pragma once

/**************************************************************************************************
CGDIBitmapPtr
Doing a regular delete on a Gdiplus::Bitmap object makes Clang warn. So instead we wrap
it up and delete with Gdiplus::Bitmap::operator delete. This was found on 2/5/2025
and no other solution could be found to keep Clang happy. It could be a problem with only
a certain version of Clang so switching to a regular delete and trying in the future might make
sense.
**************************************************************************************************/
class CGDIBitmapPtr
{
public:
    CGDIBitmapPtr()
    {
        m_pBitmap = APE_NULL;
    }
    ~CGDIBitmapPtr()
    {
        Delete();
    }

    APE_INLINE Gdiplus::Bitmap * operator ->() const
    {
        return m_pBitmap;
    }

    APE_INLINE operator Gdiplus::Bitmap * () const
    {
        return m_pBitmap;
    }

    APE_INLINE void Assign(Gdiplus::Bitmap * pObject)
    {
        Delete();
        m_pBitmap = pObject;
    }

    APE_INLINE void Delete()
    {
        if (m_pBitmap != APE_NULL)
        {
            #if _MSC_VER >= 1950
                // Visual Studio 2026 just allows a regular delete
                delete m_pBitmap;
            #else
                // Visual Studio 2022 requires this or else Clang loses it and warns about the delete
                Gdiplus::Bitmap::operator delete(m_pBitmap);
            #endif

            // reset pointer
            m_pBitmap = APE_NULL;
        }
    }

private:
    Gdiplus::Bitmap * m_pBitmap;
};
